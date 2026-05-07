export type MealType = 'NONE' | 'VEG' | 'NON_VEG';
export type MealFilterType = 'ALL' | 'VEG' | 'NON_VEG';

export interface MealOption {
  id: string;
  name: string;
  type: MealType;
  price: number;
  image: string;
}

export const MEAL_OPTIONS: MealOption[] = [
  {
    id: 'NONE',
    name: 'No Meal',
    type: 'NONE',
    price: 0,
    image: 'https://media.istockphoto.com/id/538395075/vector/sad-face-draw-on-white-plate-with-spoon-and-fork.jpg?s=1024x1024&w=is&k=20&c=X2nH6lN36Mfk6UnQU8h8t6GFb27G01fbyx51XmSB1gg='
  },
  {
    id: 'VEG_1',
    name: 'Veg Thali',
    type: 'VEG',
    price: 320,
    image: 'https://images.unsplash.com/photo-1546833999-b9f581a1996d?auto=format&fit=crop&w=500&q=80'
  },
  {
    id: 'VEG_2',
    name: 'Paneer Wrap',
    type: 'VEG',
    price: 260,
    image: 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=500&q=80'
  },
  {
    id: 'NONVEG_1',
    name: 'Chicken Rice Bowl',
    type: 'NON_VEG',
    price: 380,
    image: 'https://images.unsplash.com/photo-1516684732162-798a0062be99?auto=format&fit=crop&w=500&q=80'
  },
  {
    id: 'NONVEG_2',
    name: 'Grilled Fish',
    type: 'NON_VEG',
    price: 420,
    image: 'https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?auto=format&fit=crop&w=500&q=80'
  }
];
